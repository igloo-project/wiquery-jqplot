package nl.topicus.wqplot.components;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.model.IModel;
import org.wicketstuff.wiquery.core.javascript.JsStatement;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import nl.topicus.wqplot.components.plugins.DefaultPlugins;
import nl.topicus.wqplot.components.plugins.IPlugin;
import nl.topicus.wqplot.components.plugins.IPluginResolver;
import nl.topicus.wqplot.components.plugins.JQPlotCanvasTextRendererResourceReference;
import nl.topicus.wqplot.data.Series;
import nl.topicus.wqplot.options.PlotOptions;
import nl.topicus.wqplot.options.PluginReferenceSerializer;

public class JQPlot extends WebMarkupContainer implements IPluginResolver
{
	private static final long serialVersionUID = 1L;

	private PlotOptions options = new PlotOptions();

	private Map<String, IPlugin> plugins = new HashMap<String, IPlugin>();

	private List<IPluginResolver> resolvers = new ArrayList<IPluginResolver>();

	private List<String> afterRenderStatements = new ArrayList<String>();

	private boolean catchErrors = true;

	public JQPlot(String id, IModel< ? extends Collection< ? extends Series< ? , ? , ? >>> model)
	{
		super(id, model);
		resolvers.add(new DefaultPlugins());
		setOutputMarkupId(true);
	}

	public boolean isCatchErrors()
	{
		return catchErrors;
	}

	public JQPlot setCatchErrors(boolean catchErrors)
	{
		this.catchErrors = catchErrors;
		return this;
	}

	public PlotOptions getOptions()
	{
		return options;
	}

	@SuppressWarnings("unchecked")
	private Collection< ? extends Series< ? , ? , ? >> getModelObject()
	{
		return (Collection< ? extends Series< ? , ? , ? >>) getDefaultModelObject();
	}

	@Override
	public void renderHead(IHeaderResponse headerResponse)
	{
		/**
		 * HACK KOBALT : We do not support IE 9 anymore
		 */
//		ClientInfo info = WebSession.get().getClientInfo();
//
//		if (info instanceof WebClientInfo)
//		{
//			/**
//			 * only IE 9 supports canvas natively.
//			 */
//			WebClientInfo webinfo = (WebClientInfo) info;
//			if (webinfo.getProperties().isBrowserInternetExplorer()
//				&& webinfo.getProperties().getBrowserVersionMajor() < 9)
//				// wiQueryResourceManager.addJavaScriptResource(JQPlotExcanvasJavaScriptResourceReference.get());
//				headerResponse.render(JavaScriptHeaderItem
//					.forReference(JQPlotExcanvasJavaScriptResourceReference.get()));
//		}

		// wiQueryResourceManager.addJavaScriptResource(JQPlotJavaScriptResourceReference.get());
		// wiQueryResourceManager.addCssResource(JQPlotStyleSheetResourceReference.get());
		// wiQueryResourceManager.addJavaScriptResource(JQPlotCanvasTextRendererResourceReference.get());
		headerResponse
			.render(JavaScriptHeaderItem.forReference(JQPlotJavaScriptResourceReference.get()));
		headerResponse.render(CssHeaderItem.forReference(JQPlotStyleSheetResourceReference.get()));
		headerResponse.render(
			JavaScriptHeaderItem.forReference(JQPlotCanvasTextRendererResourceReference.get()));

		headerResponse.render(OnDomReadyHeaderItem.forScript(statement().render()));

		try
		{
			addPlugins(headerResponse);
		}
		catch (IllegalAccessException e)
		{
			throw new RuntimeException(e);
		}
	}

	private void addPlugins(IHeaderResponse headerResponse) throws IllegalAccessException
	{
		LinkedList<Object> remaining = new LinkedList<Object>();
		remaining.add(options);
		while (!remaining.isEmpty())
		{
			Object curOptionObject = remaining.removeFirst();
			Class< ? > curOptionClass = curOptionObject.getClass();
			while (curOptionClass != Object.class)
			{
				for (Field curField : curOptionClass.getDeclaredFields())
				{
					if (Modifier.isStatic(curField.getModifiers()))
						continue;
					curField.setAccessible(true);
					Object value = curField.get(curOptionObject);
					if (value == null)
						continue;

					if (value instanceof Iterable< ? >)
					{
						for (Object curItem : (Iterable< ? >) value)
							remaining.add(curItem);
					}
					else if (value instanceof String)
					{
						JsonSerialize serialize = curField.getAnnotation(JsonSerialize.class);
						if (serialize != null
							&& serialize.using() == PluginReferenceSerializer.class)
							addPlugin(headerResponse, (String) value);
					}
					else if (!(value instanceof Number) && !(value instanceof Boolean))
						remaining.add(value);
				}
				curOptionClass = curOptionClass.getSuperclass();
			}
		}
	}

	private void addPlugin(IHeaderResponse headerResponse, String plugin)
	{
		if (plugins.containsKey(plugin))
		{
			// wiQueryResourceManager.addJavaScriptResource(getPlugin(plugin).getJavaScriptResourceReference());
			headerResponse.render(JavaScriptHeaderItem
				.forReference(getPlugin(plugin).getJavaScriptResourceReference()));
			return;
		}

		for (IPluginResolver pluginResolver : resolvers)
		{
			IPlugin iPlugin = pluginResolver.getPlugin(plugin);
			if (iPlugin != null)
			{
				// wiQueryResourceManager.addJavaScriptResource(iPlugin.getJavaScriptResourceReference());
				headerResponse.render(
					JavaScriptHeaderItem.forReference(iPlugin.getJavaScriptResourceReference()));
				return;
			}
		}
		throw new IllegalArgumentException("Unknown plugin '" + plugin + "'");
	}

	/**
	 * Allows to register a new plugin.
	 *
	 * @param iPlugin
	 */
	public void registerPlugin(IPlugin iPlugin)
	{
		if (iPlugin == null)
			throw new IllegalArgumentException("Plugin cannot be null!");
		plugins.put(iPlugin.getName(), iPlugin);
	}

	/**
	 * Allows to register a new resolver.
	 *
	 * @param resolver
	 */
	public void registerPluginResolver(IPluginResolver resolver)
	{
		if (resolver == null)
			throw new IllegalArgumentException("Resolver cannot be null!");
		resolvers.add(resolver);
	}

	public void addAfterRenderStatement(String statement)
	{
		afterRenderStatements.add(statement);
	}

	@Override
	public IPlugin getPlugin(String name)
	{
		return plugins.get(name);
	}

	public JsStatement statement()
	{
		ObjectMapper mapper = new ObjectMapper();
		mapper.setSerializationInclusion(Include.NON_NULL);
		String optionsStr = "{}";
		String plotDataStr = "[]";
		try
		{
			optionsStr = mapper.writeValueAsString(options);
			plotDataStr = mapper.writeValueAsString(getModelObject());
		}
		catch (JsonGenerationException e)
		{
			e.printStackTrace();
		}
		catch (JsonMappingException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		JsStatement jsStatement =
			new JsStatement().append("$.jqplot.config.catchErrors = " + isCatchErrors() + ";\n");
		jsStatement.append("var " + getMarkupId() + " = $.jqplot('" + getMarkupId() + "', "
			+ plotDataStr + ", " + optionsStr + ");\n");

		for (String statement : afterRenderStatements)
			jsStatement.append(statement);

		return jsStatement;
	}

}
